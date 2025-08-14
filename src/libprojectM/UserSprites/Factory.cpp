#include "UserSprites/Factory.hpp"

#include "UserSprites/MilkdropSprite.hpp"

#include <Utils.hpp>

namespace libprojectM {
namespace UserSprites {

auto Factory::CreateSprite(const std::string& type) -> Sprite::Ptr
{
    // Android TV optimization: Early validation and memory check
    if (type.empty() || type.length() > 64)
    {
        return {};
    }

    const auto lowerCaseType = Utils::ToLower(type);

    if (lowerCaseType == "milkdrop")
    {
        try
        {
            return std::make_unique<MilkdropSprite>();
        }
        catch (const std::bad_alloc&)
        {
            // Android TV: Handle memory allocation failures gracefully
            return {};
        }
        catch (...)
        {
            // Android TV: Catch any other exceptions during sprite creation
            return {};
        }
    }

    return {};
}


} // namespace UserSprites
} // namespace libprojectM
